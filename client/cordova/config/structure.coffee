# Read more about app structure at http://docs.appgyver.com

module.exports =

  # See styling options for tabs and other native components in app/common/native-styles/ios.css or app/common/native-styles/android.css
  tabs: [
    {
      title: "Заказ"
      id: "order"
      location: "example#order" # Supersonic module#view type navigation
    }
    {
      title: "История"
      id: "history"
      location: "example#history"
    }
    {
      title: "О приложение"
      id: "about"
      location: "example#about"
    }
  ]

  # rootView:
  #   location: "example#getting-started"

  preloads: [
    {
      id: "options"
      location: "example#options"
    }
    {
      id: "using-the-scanner"
      location: "example#using-the-scanner"
    }
  ]

  # drawers:
  #   left:
  #     id: "leftDrawer"
  #     location: "example#drawer"
  #     showOnAppLoad: false
  #   options:
  #     animation: "swingingDoor"
  #
  # initialView:
  #   id: "initialView"
  #   location: "example#initial-view"
