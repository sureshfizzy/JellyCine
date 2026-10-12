#ifndef JELLYCINE_SCREEN_H
#define JELLYCINE_SCREEN_H

#include <stdbool.h>

#define MAX_SCREEN_STACK 8

typedef struct Screen Screen;

struct Screen {
    const char *name;
    void *state;
    void (*on_enter)(Screen *self);
    void (*on_exit)(Screen *self);
    void (*on_input)(Screen *self, int key);
    void (*draw)(Screen *self);
    void (*destroy)(Screen *self);
};

void screen_init(void);
void screen_shutdown(void);
void screen_push(Screen *s);
Screen *screen_pop(void);
Screen *screen_current(void);
int screen_depth(void);

#endif