#ifndef JELLYCINE_FOCUS_H
#define JELLYCINE_FOCUS_H

#include <stdbool.h>

#define MAX_FOCUS_ROWS 32
#define MAX_FOCUS_COLS 32

typedef struct {
    int rows;
    int cols;
    int row;
    int col;
    bool wrap_h;
    bool wrap_v;
} FocusGrid;

void focus_init(FocusGrid *g, int rows, int cols);
bool focus_move(FocusGrid *g, int drow, int dcol);
int focus_index(const FocusGrid *g);
void focus_set(FocusGrid *g, int row, int col);

#endif