#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#define NIKKI_MAX 7
#define NIKKI_MAXSZ 0x500
#define NIKKI_MINSZ 0x8

char *nikkis[NIKKI_MAX];
int sizes[NIKKI_MAX];

void predict() {
  char* test = nikkis[0];
  if (test == NULL){
    printf("cant predict");
    return;
  }
  unsigned short secret = ((unsigned short)test) >> 12;
  printf("TAKE THIS: %x\n", secret);
  return;
}

void menu() {
  printf("[1] add\n"
         "[2] delete\n"
         "[3] edit\n"
         ">> ");
}

void add() {
  int idx, size;

  printf("idx: ");
  scanf("%d", &idx);

  if (idx < 0 || idx >= NIKKI_MAX) {
    printf("illegal");
    return;
  }

  printf("size: ");
  scanf("%d", &size);

  if (size < 0 || size > NIKKI_MAXSZ) {
    printf("illegal");
    return;
  }

  char *nikki = malloc(size);
  if (nikki == NULL) {
    printf("add note fail");
    return;
  }

  nikkis[idx] = nikki;
  sizes[idx] = size;
}

void delete() {
  int idx;
  printf("idx: ");
  scanf("%d", &idx);

  if (idx < 0 || idx >= NIKKI_MAX) {
    printf("illegal");
    return;
  }

  free(nikkis[idx]);
}

void edit() {
  int idx;
  printf("idx: ");
  scanf("%d", &idx);

  if (idx < 0 || idx >= NIKKI_MAX) {
    printf("illegal");
    return;
  }
  printf("content: ");

  read(0, nikkis[idx], sizes[idx]);
}

void vuln() {
  printf("yo\n");
  int choice;
  while (1) {
    menu();
    scanf("%d", &choice);
    switch (choice) {
    case 1:
      add();
      break;
    case 2:
      delete();
      break;
    case 3:
      edit();
      break;
    case 4:
      predict();
      break;
    default:
      printf("what\n");
    }
  }
}

int main() {
  setvbuf(stdin, NULL, _IONBF, 0);
  setvbuf(stdout, NULL, _IONBF, 0);
  setvbuf(stderr, NULL, _IONBF, 0);
  vuln();
}
