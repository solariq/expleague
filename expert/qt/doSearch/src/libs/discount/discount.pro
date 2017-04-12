TEMPLATE = lib

TARGET = discount

CONFIG += staticlib


SOURCES = \
     mkdio.c markdown.c dumptree.c generate.c \
     resource.c docheader.c version.c css.c \
     xml.c Csio.c xmlpage.c basename.c emmatch.c \
     github_flavoured.c setup.c tags.c html5.c flags.c

HEADERS = \
    ./config.h \
    ./amalloc.h \
    ./cstring.h \
    ./markdown.h \
    ./pgm_options.h \
    ./tags.h \
    ./blocktags \
    ./mkdio.h
