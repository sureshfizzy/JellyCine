#include "jellyfin.h"
#include "webos.h"
#include "ui/renderer.h"
#include "ui/text.h"
#include "ui/screen.h"
#include "ui/focus.h"

#include <SDL2/SDL.h>
#include <GLES2/gl2.h>
#include <signal.h>
#include <stdio.h>
#include <stdbool.h>

static bool s_running = true;
static JellyfinClient *s_jf;
static bool s_connected;
static bool s_authenticated;
static JfAuthResult s_auth;
static JfLibraryList s_libs;

static void on_signal(int sig) {
    (void)sig;
    s_running = false;
}

static void draw_status(void) {
    if (!s_connected) {
        text_draw("Connecting to server...", 80, 500, FONT_SIZE_TITLE,
                  0.6f, 0.6f, 0.6f, 1.0f);
        return;
    }
    if (!s_authenticated) {
        text_draw("Authentication failed", 80, 500, FONT_SIZE_TITLE,
                  1.0f, 0.3f, 0.3f, 1.0f);
        return;
    }

    text_draw("JellyCine", 80, 40, FONT_SIZE_HERO,
              1.0f, 1.0f, 1.0f, 1.0f);

    char welcome[128];
    snprintf(welcome, sizeof(welcome), "Welcome, %s",
             s_auth.user.name ? s_auth.user.name : "User");
    text_draw(welcome, 80, 130, FONT_SIZE_BODY,
              0.7f, 0.7f, 0.7f, 1.0f);

    float y = 220;
    text_draw("Libraries", 80, y, FONT_SIZE_TITLE,
              1.0f, 1.0f, 1.0f, 1.0f);
    y += 70;

    for (int i = 0; i < s_libs.count; i++) {
        float card_x = 80;
        float card_y = y + (float)(i * 90);
        float card_w = 400;
        float card_h = 76;

        renderer_draw_rrect(card_x, card_y, card_w, card_h, 12,
                            0.15f, 0.15f, 0.22f, 1.0f);

        text_draw(s_libs.items[i].name, card_x + 20, card_y + 12,
                  FONT_SIZE_BODY, 1.0f, 1.0f, 1.0f, 1.0f);

        const char *type = s_libs.items[i].collection_type;
        if (type) {
            text_draw(type, card_x + 20, card_y + 46,
                      FONT_SIZE_SMALL, 0.5f, 0.5f, 0.6f, 1.0f);
        }
    }
}

int main(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);

    fprintf(stdout, "JellyCine %s starting\n", APP_VERSION);

    webos_init();

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS) < 0) {
        fprintf(stderr, "SDL_Init: %s\n", SDL_GetError());
        return 1;
    }

    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 2);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0);

    SDL_Window *window = SDL_CreateWindow("JellyCine",
        SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
        1920, 1080, SDL_WINDOW_OPENGL | SDL_WINDOW_FULLSCREEN);

    if (!window) {
        fprintf(stderr, "SDL_CreateWindow: %s\n", SDL_GetError());
        SDL_Quit();
        return 1;
    }

    SDL_GLContext gl = SDL_GL_CreateContext(window);
    SDL_GL_SetSwapInterval(1);

    if (!renderer_init()) {
        fprintf(stderr, "renderer_init failed\n");
        goto cleanup;
    }

    if (!text_init("/usr/share/fonts/truetype/DroidSansFallback.ttf")) {
        fprintf(stderr, "text_init failed, trying bundled font\n");
        if (!text_init("assets/Inter.ttf")) {
            fprintf(stderr, "text_init failed\n");
            goto cleanup;
        }
    }

    screen_init();

    s_jf = jf_client_create("http://192.168.1.100:8096");
    s_connected = s_jf && jf_connect(s_jf);

    if (s_connected) {
        webos_toast("Connected to Jellyfin");
        s_authenticated = jf_authenticate(s_jf, "demo", "demo", &s_auth);
        if (s_authenticated)
            jf_get_libraries(s_jf, &s_libs);
    }

    FocusGrid focus;
    focus_init(&focus, s_libs.count > 0 ? s_libs.count : 1, 1);

    while (s_running) {
        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_QUIT) {
                s_running = false;
                break;
            }
            if (ev.type == SDL_KEYDOWN) {
                switch (ev.key.keysym.sym) {
                case SDLK_ESCAPE:
                case SDLK_AC_BACK:
                    if (screen_depth() > 1)
                        screen_pop();
                    else
                        s_running = false;
                    break;
                case SDLK_UP:
                    focus_move(&focus, -1, 0);
                    break;
                case SDLK_DOWN:
                    focus_move(&focus, 1, 0);
                    break;
                case SDLK_LEFT:
                    focus_move(&focus, 0, -1);
                    break;
                case SDLK_RIGHT:
                    focus_move(&focus, 0, 1);
                    break;
                case SDLK_RETURN:
                case SDLK_KP_ENTER:
                    break;
                default:
                    break;
                }
            }
        }

        glViewport(0, 0, SCREEN_W, SCREEN_H);
        glClearColor(0.06f, 0.06f, 0.11f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        draw_status();

        if (s_authenticated && s_libs.count > 0) {
            int fi = focus_index(&focus);
            float fy = 290.0f + (float)(fi * 90);
            renderer_draw_rrect(76, fy - 2, 408, 80, 14,
                                0.3f, 0.5f, 1.0f, 0.4f);
        }

        text_frame_advance();
        SDL_GL_SwapWindow(window);
    }

cleanup:
    if (s_authenticated) {
        jf_library_list_free(&s_libs);
        jf_auth_result_free(&s_auth);
    }
    jf_client_destroy(s_jf);

    screen_shutdown();
    text_shutdown();
    renderer_shutdown();

    SDL_GL_DeleteContext(gl);
    SDL_DestroyWindow(window);
    SDL_Quit();
    webos_shutdown();

    fprintf(stdout, "JellyCine shutdown\n");
    return 0;
}