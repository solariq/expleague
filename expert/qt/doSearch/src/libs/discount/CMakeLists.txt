SET(discount_sources mkdio.c markdown.c dumptree.c generate.c
        resource.c docheader.c version.c css.c
        xml.c Csio.c xmlpage.c basename.c emmatch.c
        github_flavoured.c setup.c tags.c html5.c flags.c)

ADD_LIBRARY(discount ${discount_sources})
