// NestedLoops: 16x16 matrix add and sum, repeated 8 times.
//
// Branch behaviour: almost only loop branches with fixed trip counts (16, 256),
// which a predictor learns quickly. Expect a low misprediction rate; good for
// showing the difference between 1-bit and 2-bit counters (1-bit counters miss
// twice per inner loop, 2-bit counters once).
//
// Self-checking: returns 0 (PASS) if the total is correct.
// Uses only RV32I: indices use shifts instead of multiply, no printf, no C library.

#define ROWS   16
#define COLS   16
#define ROUNDS 8
#define IDX(i, j) (((i) << 4) + (j))     // i * 16 + j

#define CONSOLE (*(volatile unsigned char *)0x40)

static void print(const char *s)
{
    while (*s)
        CONSOLE = *s++;
}

int a[ROWS * COLS];
int b[ROWS * COLS];
int c[ROWS * COLS];

int main(void)
{
    int total = 0;

    print("NestedLoops: 16x16 matrix add, 8 rounds\n");

    for (int i = 0; i < ROWS; i++)
        for (int j = 0; j < COLS; j++) {
            a[IDX(i, j)] = i + j;
            b[IDX(i, j)] = i - j;
        }

    for (int r = 0; r < ROUNDS; r++) {
        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLS; j++)
                c[IDX(i, j)] = a[IDX(i, j)] + b[IDX(i, j)];      // = 2 * i

        for (int k = 0; k < ROWS * COLS; k++)
            total += c[k];
    }

    // per round: sum over i, j of 2i = 2 * 16 * (0 + 1 + ... + 15) = 3840
    int ok = (total == ROUNDS * 3840);

    print(ok ? "NestedLoops: OK\n" : "NestedLoops: FAILED\n");
    return ok ? 0 : 1;
}
