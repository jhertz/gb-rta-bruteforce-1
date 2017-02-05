#!/bin/bash
clang `sdl-config --cflags --libs` sdl_test.c -o sdl_test