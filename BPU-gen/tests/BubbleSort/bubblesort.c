// BubbleSort: sorts 64 pseudo-random numbers.
//
// Branch behaviour: the inner comparison (data[j] > data[j+1]) depends on the
// data, so it is hard to predict, especially in the first passes. The loop
// branches are easy. Expect a medium-to-high misprediction rate.
//
// Self-checking: returns 0 (PASS) if the result is sorted and no value was lost.
// Uses only RV32I: no multiply/divide, no printf, no C library.

#define N 64

#define CONSOLE (*(volatile unsigned char *)0x40)

static void print(const char *s)
{
    while (*s)
        CONSOLE = *s++;
}

static unsigned int seed = 0x12345678;

// xorshift32: random numbers with shifts and xor only
static unsigned int next_random(void)
{
    seed ^= seed << 13;
    seed ^= seed >> 17;
    seed ^= seed << 5;
    return seed;
}

unsigned int data[N];

int main(void)
{
    unsigned int sum_before = 0;
    unsigned int sum_after = 0;
    int errors = 0;

    print("BubbleSort: sorting 64 numbers\n");

    for (int i = 0; i < N; i++) {
        data[i] = next_random() & 0xFFFF;
        sum_before += data[i];
    }

    for (int i = 0; i < N - 1; i++) {
        int swapped = 0;
        for (int j = 0; j < N - 1 - i; j++) {
            if (data[j] > data[j + 1]) {
                unsigned int t = data[j];
                data[j] = data[j + 1];
                data[j + 1] = t;
                swapped = 1;
            }
        }
        if (!swapped)
            break;
    }

    for (int i = 0; i < N; i++) {
        sum_after += data[i];
        if (i > 0 && data[i - 1] > data[i])
            errors++;
    }
    if (sum_after != sum_before)
        errors++;

    print(errors ? "BubbleSort: FAILED\n" : "BubbleSort: OK\n");
    return errors;
}
