#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "sc.h"
#include "selftest.h"

struct test_vector {
    unsigned char request[16];
    unsigned char response[16];
};

static const struct test_vector selftest = { {REQ_BYTES}, {EXP_BYTES} };

typedef void (__fastcall *transform_fn)(const unsigned char *request, unsigned char *response);

int main(void){
    unsigned char out[16] = {0};
    void *m = VirtualAlloc(0, sizeof sc, MEM_COMMIT|MEM_RESERVE, PAGE_EXECUTE_READWRITE);
    if(!m) return 1;
    memcpy(m, sc, sizeof sc);
    ((transform_fn)m)(selftest.request, out);
    VirtualFree(m, 0, MEM_RELEASE);
    if(memcmp(out, selftest.response, 16) == 0) printf("self-test ok\n");
    else printf("self-test failed\n");
    return 0;
}
