#include "screen.h"
#include <stdio.h>
#include <string.h>

static Screen *s_stack[MAX_SCREEN_STACK];
static int s_depth;

void screen_init(void) {
    memset(s_stack, 0, sizeof(s_stack));
    s_depth = 0;
}

void screen_shutdown(void) {
    while (s_depth > 0) {
        Screen *s = s_stack[--s_depth];
        if (s->on_exit) s->on_exit(s);
        if (s->destroy) s->destroy(s);
    }
}

void screen_push(Screen *s) {
    if (s_depth >= MAX_SCREEN_STACK) {
        fprintf(stderr, "Screen stack overflow\n");
        return;
    }
    s_stack[s_depth++] = s;
    if (s->on_enter) s->on_enter(s);
}

Screen *screen_pop(void) {
    if (s_depth <= 0) return NULL;
    Screen *s = s_stack[--s_depth];
    if (s->on_exit) s->on_exit(s);
    if (s_depth > 0 && s_stack[s_depth - 1]->on_enter)
        s_stack[s_depth - 1]->on_enter(s_stack[s_depth - 1]);
    return s;
}

Screen *screen_current(void) {
    return s_depth > 0 ? s_stack[s_depth - 1] : NULL;
}

int screen_depth(void) {
    return s_depth;
}