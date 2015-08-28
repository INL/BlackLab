# If you just type 'make', build the debug version
all: debug

# Debug compile (-ggdb = include gdb debugger symbols)
debug:
	gcc -ggdb -Wall -o vmtouch vmtouch.c

# Release compile (-O3 = optimize)
release:
	gcc -Wall -O3 -o vmtouch vmtouch.c

