find_package(Qt5 REQUIRED COMPONENTS Core Xml Network)

file(GLOB BASE_SRC "src/base/*.cpp")
file(GLOB CLIENT_SRC "src/client/*.cpp")

#message("${BASE_SRC}")
set(CMAKE_AUTOMOC ON)

add_library(qxmpp ${BASE_SRC} ${CLIENT_SRC})
target_include_directories(qxmpp PRIVATE src/base src/client)
target_link_libraries(qxmpp
  Qt5::Core
  Qt5::Xml
  Qt5::Network
)
