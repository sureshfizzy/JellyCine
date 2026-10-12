#ifndef JELLYCINE_RENDERER_H
#define JELLYCINE_RENDERER_H

#include <GLES2/gl2.h>
#include <stdbool.h>
#include <stdint.h>

#define SCREEN_W 1920
#define SCREEN_H 1080

bool renderer_init(void);
void renderer_shutdown(void);

void renderer_draw_rect(float x, float y, float w, float h,
                        float r, float g, float b, float a);

void renderer_draw_rrect(float x, float y, float w, float h,
                         float radius,
                         float r, float g, float b, float a);

void renderer_draw_texture(GLuint tex, float x, float y, float w, float h,
                           float tint_r, float tint_g, float tint_b, float tint_a);

void renderer_draw_texture_rounded(GLuint tex, float x, float y, float w, float h,
                                   float radius,
                                   float tint_r, float tint_g, float tint_b, float tint_a);

void renderer_draw_text_texture(GLuint tex, float x, float y, float w, float h,
                                float r, float g, float b, float a);

GLuint renderer_upload_rgba(const uint8_t *pixels, int w, int h);
GLuint renderer_upload_alpha(const uint8_t *pixels, int w, int h);
void renderer_delete_texture(GLuint tex);

#endif