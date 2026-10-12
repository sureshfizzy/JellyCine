#include "renderer.h"
#include <stdio.h>
#include <string.h>

static GLuint s_vbo;

static GLuint s_prog_flat;
static GLint  s_flat_rect, s_flat_screen, s_flat_col;

static GLuint s_prog_rect;
static GLint  s_rect_rect, s_rect_screen, s_rect_col, s_rect_radius, s_rect_size;

static GLuint s_prog_tex;
static GLint  s_tex_rect, s_tex_screen, s_tex_tex, s_tex_tint, s_tex_card;

static GLuint s_prog_text;
static GLint  s_text_rect, s_text_screen, s_text_tex, s_text_col;

static const char *VS_RECT =
    "attribute vec2 a_pos;\n"
    "uniform vec4 u_rect;\n"
    "uniform vec2 u_screen;\n"
    "varying vec2 v_uv;\n"
    "void main(){\n"
    "  v_uv = a_pos;\n"
    "  vec2 px = u_rect.xy + a_pos * u_rect.zw;\n"
    "  vec2 ndc = px / u_screen * 2.0 - 1.0;\n"
    "  gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);\n"
    "}\n";

static const char *FS_FLAT =
    "precision mediump float;\n"
    "uniform vec4 u_col;\n"
    "void main(){ gl_FragColor = u_col; }\n";

/* SDF rounded rect with 2px AA band */
static const char *FS_RECT =
    "precision mediump float;\n"
    "varying vec2 v_uv;\n"
    "uniform vec4 u_col;\n"
    "uniform float u_radius;\n"
    "uniform vec2 u_size;\n"
    "void main(){\n"
    "  vec2 p = (v_uv - 0.5) * u_size;\n"
    "  vec2 d = abs(p) - (u_size * 0.5 - u_radius);\n"
    "  float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - u_radius;\n"
    "  float aa = 1.0 - smoothstep(-1.0, 1.0, dist);\n"
    "  gl_FragColor = vec4(u_col.rgb, u_col.a * aa);\n"
    "}\n";

static const char *VS_TEX =
    "attribute vec2 a_pos;\n"
    "uniform vec4 u_rect;\n"
    "uniform vec2 u_screen;\n"
    "varying vec2 v_uv;\n"
    "varying highp vec2 v_p;\n"
    "void main(){\n"
    "  v_uv = a_pos;\n"
    "  v_p = (a_pos - 0.5) * u_rect.zw;\n"
    "  vec2 px = u_rect.xy + a_pos * u_rect.zw;\n"
    "  vec2 ndc = px / u_screen * 2.0 - 1.0;\n"
    "  gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);\n"
    "}\n";

/* Textured quad with optional SDF rounded corners */
static const char *FS_TEX =
    "precision mediump float;\n"
    "varying vec2 v_uv;\n"
    "varying highp vec2 v_p;\n"
    "uniform sampler2D u_tex;\n"
    "uniform vec4 u_tint;\n"
    "uniform highp vec3 u_card;\n" /* half_w - radius, half_h - radius, radius */
    "void main(){\n"
    "  vec4 c = texture2D(u_tex, v_uv) * u_tint;\n"
    "  if (u_card.z < 0.5) { gl_FragColor = c; return; }\n"
    "  highp vec2 q = abs(v_p) - u_card.xy;\n"
    "  highp float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - u_card.z;\n"
    "  float m = 1.0 - smoothstep(-1.0, 1.0, d);\n"
    "  gl_FragColor = vec4(c.rgb, c.a * m);\n"
    "}\n";

/* Alpha texture tinted by uniform color */
static const char *FS_TEXT =
    "precision mediump float;\n"
    "varying highp vec2 v_uv;\n"
    "uniform sampler2D u_tex;\n"
    "uniform vec4 u_col;\n"
    "void main(){\n"
    "  float a = texture2D(u_tex, v_uv).a;\n"
    "  gl_FragColor = vec4(u_col.rgb, u_col.a * a);\n"
    "}\n";

static GLuint compile_shader(GLenum type, const char *src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, NULL);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(sh, sizeof(log), NULL, log);
        fprintf(stderr, "Shader compile error: %s\n", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

static GLuint link_program(const char *vs_src, const char *fs_src) {
    GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_src);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_src);
    if (!vs || !fs) return 0;

    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glBindAttribLocation(prog, 0, "a_pos");
    glLinkProgram(prog);
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetProgramInfoLog(prog, sizeof(log), NULL, log);
        fprintf(stderr, "Program link error: %s\n", log);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
}

bool renderer_init(void) {
    static const float quad[] = { 0, 0, 1, 0, 0, 1, 1, 1 };
    glGenBuffers(1, &s_vbo);
    glBindBuffer(GL_ARRAY_BUFFER, s_vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);

    glEnable(GL_BLEND);
    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                        GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_DITHER);

    s_prog_flat = link_program(VS_RECT, FS_FLAT);
    s_prog_rect = link_program(VS_RECT, FS_RECT);
    s_prog_tex  = link_program(VS_TEX, FS_TEX);
    s_prog_text = link_program(VS_TEX, FS_TEXT);

    if (!s_prog_flat || !s_prog_rect || !s_prog_tex || !s_prog_text) {
        fprintf(stderr, "Failed to compile shaders\n");
        return false;
    }

    s_flat_rect   = glGetUniformLocation(s_prog_flat, "u_rect");
    s_flat_screen = glGetUniformLocation(s_prog_flat, "u_screen");
    s_flat_col    = glGetUniformLocation(s_prog_flat, "u_col");

    s_rect_rect   = glGetUniformLocation(s_prog_rect, "u_rect");
    s_rect_screen = glGetUniformLocation(s_prog_rect, "u_screen");
    s_rect_col    = glGetUniformLocation(s_prog_rect, "u_col");
    s_rect_radius = glGetUniformLocation(s_prog_rect, "u_radius");
    s_rect_size   = glGetUniformLocation(s_prog_rect, "u_size");

    s_tex_rect   = glGetUniformLocation(s_prog_tex, "u_rect");
    s_tex_screen = glGetUniformLocation(s_prog_tex, "u_screen");
    s_tex_tex    = glGetUniformLocation(s_prog_tex, "u_tex");
    s_tex_tint   = glGetUniformLocation(s_prog_tex, "u_tint");
    s_tex_card   = glGetUniformLocation(s_prog_tex, "u_card");

    s_text_rect   = glGetUniformLocation(s_prog_text, "u_rect");
    s_text_screen = glGetUniformLocation(s_prog_text, "u_screen");
    s_text_tex    = glGetUniformLocation(s_prog_text, "u_tex");
    s_text_col    = glGetUniformLocation(s_prog_text, "u_col");

    return true;
}

void renderer_shutdown(void) {
    glDeleteProgram(s_prog_flat);
    glDeleteProgram(s_prog_rect);
    glDeleteProgram(s_prog_tex);
    glDeleteProgram(s_prog_text);
    glDeleteBuffers(1, &s_vbo);
}

void renderer_draw_rect(float x, float y, float w, float h,
                        float r, float g, float b, float a) {
    glUseProgram(s_prog_flat);
    glUniform4f(s_flat_rect, x, y, w, h);
    glUniform2f(s_flat_screen, SCREEN_W, SCREEN_H);
    glUniform4f(s_flat_col, r, g, b, a);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void renderer_draw_rrect(float x, float y, float w, float h,
                         float radius,
                         float r, float g, float b, float a) {
    if (radius < 0.5f) {
        renderer_draw_rect(x, y, w, h, r, g, b, a);
        return;
    }
    glUseProgram(s_prog_rect);
    glUniform4f(s_rect_rect, x, y, w, h);
    glUniform2f(s_rect_screen, SCREEN_W, SCREEN_H);
    glUniform4f(s_rect_col, r, g, b, a);
    glUniform1f(s_rect_radius, radius);
    glUniform2f(s_rect_size, w, h);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

static void draw_tex_impl(GLuint tex, float x, float y, float w, float h,
                          float radius,
                          float tr, float tg, float tb, float ta) {
    glUseProgram(s_prog_tex);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    glUniform4f(s_tex_rect, x, y, w, h);
    glUniform2f(s_tex_screen, SCREEN_W, SCREEN_H);
    glUniform1i(s_tex_tex, 0);
    glUniform4f(s_tex_tint, tr, tg, tb, ta);
    glUniform3f(s_tex_card, w * 0.5f - radius, h * 0.5f - radius, radius);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void renderer_draw_texture(GLuint tex, float x, float y, float w, float h,
                           float tr, float tg, float tb, float ta) {
    draw_tex_impl(tex, x, y, w, h, 0, tr, tg, tb, ta);
}

void renderer_draw_texture_rounded(GLuint tex, float x, float y, float w, float h,
                                   float radius,
                                   float tr, float tg, float tb, float ta) {
    draw_tex_impl(tex, x, y, w, h, radius, tr, tg, tb, ta);
}

/* Used by text.c — tint alpha texture with a color */
void renderer_draw_text_texture(GLuint tex, float x, float y, float w, float h,
                                float r, float g, float b, float a) {
    glUseProgram(s_prog_text);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    glUniform4f(s_text_rect, x, y, w, h);
    glUniform2f(s_text_screen, SCREEN_W, SCREEN_H);
    glUniform1i(s_text_tex, 0);
    glUniform4f(s_text_col, r, g, b, a);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

GLuint renderer_upload_rgba(const uint8_t *pixels, int w, int h) {
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex;
}

GLuint renderer_upload_alpha(const uint8_t *pixels, int w, int h) {
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, w, h, 0, GL_ALPHA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex;
}

void renderer_delete_texture(GLuint tex) {
    if (tex) glDeleteTextures(1, &tex);
}