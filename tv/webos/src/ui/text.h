#ifndef JELLYCINE_TEXT_H
#define JELLYCINE_TEXT_H

#include <GLES2/gl2.h>
#include <stdbool.h>

typedef enum {
    FONT_SIZE_SMALL  = 24,
    FONT_SIZE_BODY   = 32,
    FONT_SIZE_TITLE  = 48,
    FONT_SIZE_HERO   = 72
} FontSize;

bool text_init(const char *font_path);
void text_shutdown(void);

void text_draw(const char *str, float x, float y, FontSize size,
               float r, float g, float b, float a);

void text_measure(const char *str, FontSize size, int *out_w, int *out_h);
void text_frame_advance(void);

#endif