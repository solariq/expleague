find_package(Qt5 REQUIRED COMPONENTS Core Xml Network)

set(BASE_SRC src/base/QXmppArchiveIq.cpp
    src/base/QXmppBindIq.cpp
    src/base/QXmppBookmarkSet.cpp
    src/base/QXmppByteStreamIq.cpp
    src/base/QXmppCodec.cpp
    src/base/QXmppConstants.cpp
    src/base/QXmppDataForm.cpp
    src/base/QXmppDiscoveryIq.cpp
    src/base/QXmppElement.cpp
    src/base/QXmppEntityTimeIq.cpp
    src/base/QXmppGlobal.cpp
    src/base/QXmppIbbIq.cpp
    src/base/QXmppIq.cpp
    src/base/QXmppJingleIq.cpp
    src/base/QXmppLogger.cpp
    src/base/QXmppMessage.cpp
    src/base/QXmppMucIq.cpp
    src/base/QXmppNonSASLAuth.cpp
    src/base/QXmppPingIq.cpp
    src/base/QXmppPresence.cpp
    src/base/QXmppPubSubIq.cpp
    src/base/QXmppRegisterIq.cpp
    src/base/QXmppResultSet.cpp
    src/base/QXmppRosterIq.cpp
    src/base/QXmppRpcIq.cpp
    src/base/QXmppRtcpPacket.cpp
    src/base/QXmppRtpChannel.cpp
    src/base/QXmppRtpPacket.cpp
    src/base/QXmppSasl.cpp
    src/base/QXmppSessionIq.cpp
    src/base/QXmppSocks.cpp
    src/base/QXmppStanza.cpp
    src/base/QXmppStream.cpp
    src/base/QXmppStreamFeatures.cpp
    src/base/QXmppStreamInitiationIq.cpp
    src/base/QXmppStun.cpp
    src/base/QXmppUtils.cpp
    src/base/QXmppVCardIq.cpp
    src/base/QXmppVersionIq.cpp)

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
