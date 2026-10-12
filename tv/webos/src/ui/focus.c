#include "focus.h"

void focus_init(FocusGrid *g, int rows, int cols) {
    g->rows = rows;
    g->cols = cols;
    g->row = 0;
    g->col = 0;
    g->wrap_h = false;
    g->wrap_v = false;
}

bool focus_move(FocusGrid *g, int drow, int dcol) {
    int nr = g->row + drow;
    int nc = g->col + dcol;

    if (g->wrap_v) {
        nr = (nr + g->rows) % g->rows;
    } else {
        if (nr < 0 || nr >= g->rows) return false;
    }

    if (g->wrap_h) {
        nc = (nc + g->cols) % g->cols;
    } else {
        if (nc < 0 || nc >= g->cols) return false;
    }

    g->row = nr;
    g->col = nc;
    return true;
}

int focus_index(const FocusGrid *g) {
    return g->row * g->cols + g->col;
}

void focus_set(FocusGrid *g, int row, int col) {
    if (row >= 0 && row < g->rows) g->row = row;
    if (col >= 0 && col < g->cols) g->col = col;
}