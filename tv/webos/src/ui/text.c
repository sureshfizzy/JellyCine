#include "text.h"
#include "renderer.h"
#include <SDL2/SDL_ttf.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#define CACHE_SLOTS 128
#define HASH_SEED 0x811c9dc5u

typedef struct {
    char *str;
    FontSize size;
    GLuint tex;
    int w, h;
    uint32_t frame;
} GlyphEntry;

static GlyphEntry s_cache[CACHE_SLOTS];
static uint32_t s_frame;

static TTF_Font *s_fonts[4];
static const int FONT_SIZES[] = { 24, 32, 48, 72 };

static uint32_t fnv1a(const char *s, int size) {
    uint32_t h = HASH_SEED;
    while (*s) {
        h ^= (uint8_t)*s++;
        h *= 0x01000193u;
    }
    h ^= (uint32_t)size;
    h *= 0x01000193u;
    return h;
}

static TTF_Font *get_font(FontSize size) {
    for (int i = 0; i < 4; i++) {
        if (FONT_SIZES[i] == (int)size) return s_fonts[i];
    }
    return s_fonts[1];
}

bool text_init(const char *font_path) {
    if (TTF_Init() < 0) {
        fprintf(stderr, "TTF_Init: %s\n", TTF_GetError());
        return false;
    }
    for (int i = 0; i < 4; i++) {
        s_fonts[i] = TTF_OpenFont(font_path, FONT_SIZES[i]);
        if (!s_fonts[i]) {
            fprintf(stderr, "TTF_OpenFont(%d): %s\n", FONT_SIZES[i], TTF_GetError());
            return false;
        }
    }
    memset(s_cache, 0, sizeof(s_cache));
    s_frame = 0;
    return true;
}

void text_shutdown(void) {
    for (int i = 0; i < CACHE_SLOTS; i++) {
        if (s_cache[i].tex) renderer_delete_texture(s_cache[i].tex);
        free(s_cache[i].str);
    }
    memset(s_cache, 0, sizeof(s_cache));
    for (int i = 0; i < 4; i++) {
        if (s_fonts[i]) TTF_CloseFont(s_fonts[i]);
        s_fonts[i] = NULL;
    }
    TTF_Quit();
}

static GlyphEntry *cache_lookup(const char *str, FontSize size) {
    uint32_t h = fnv1a(str, (int)size);
    uint32_t idx = h % CACHE_SLOTS;

    for (int probe = 0; probe < 4; probe++) {
        uint32_t slot = (idx + probe) % CACHE_SLOTS;
        GlyphEntry *e = &s_cache[slot];
        if (e->str && e->size == size && strcmp(e->str, str) == 0) {
            e->frame = s_frame;
            return e;
        }
    }
    return NULL;
}

static GlyphEntry *cache_insert(const char *str, FontSize size, GLuint tex, int w, int h) {
    uint32_t hash = fnv1a(str, (int)size);
    uint32_t idx = hash % CACHE_SLOTS;

    uint32_t best_slot = idx;
    uint32_t oldest = s_frame + 1;
    for (int probe = 0; probe < 4; probe++) {
        uint32_t slot = (idx + probe) % CACHE_SLOTS;
        GlyphEntry *e = &s_cache[slot];
        if (!e->str) {
            best_slot = slot;
            break;
        }
        if (e->frame < oldest) {
            oldest = e->frame;
            best_slot = slot;
        }
    }

    GlyphEntry *e = &s_cache[best_slot];
    if (e->tex) renderer_delete_texture(e->tex);
    free(e->str);
    e->str = strdup(str);
    e->size = size;
    e->tex = tex;
    e->w = w;
    e->h = h;
    e->frame = s_frame;
    return e;
}

static GlyphEntry *render_string(const char *str, FontSize size) {
    TTF_Font *font = get_font(size);
    SDL_Color white = { 255, 255, 255, 255 };
    SDL_Surface *surf = TTF_RenderUTF8_Blended(font, str, white);
    if (!surf) return NULL;

    /* Blended returns ARGB32; upload as RGBA, text shader samples .a only */
    GLuint tex = renderer_upload_rgba((const uint8_t *)surf->pixels, surf->w, surf->h);
    GlyphEntry *e = cache_insert(str, size, tex, surf->w, surf->h);
    SDL_FreeSurface(surf);
    return e;
}

void text_draw(const char *str, float x, float y, FontSize size,
               float r, float g, float b, float a) {
    if (!str || !*str) return;

    GlyphEntry *e = cache_lookup(str, size);
    if (!e) e = render_string(str, size);
    if (!e) return;

    renderer_draw_text_texture(e->tex, x, y, (float)e->w, (float)e->h, r, g, b, a);
}

void text_measure(const char *str, FontSize size, int *out_w, int *out_h) {
    TTF_Font *font = get_font(size);
    TTF_SizeUTF8(font, str, out_w, out_h);
}

void text_frame_advance(void) {
    s_frame++;
}