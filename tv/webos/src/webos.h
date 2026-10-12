#ifndef JELLYCINE_WEBOS_H
#define JELLYCINE_WEBOS_H

#include <stdbool.h>

bool webos_init(void);
void webos_shutdown(void);

void webos_toast(const char *message);

#endif