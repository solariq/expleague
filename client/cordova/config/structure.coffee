# Read more about app structure at http://docs.appgyver.com

module.exports =

  # See styling options for tabs and other native components in app/common/native-styles/ios.css or app/common/native-styles/android.css
  tabs: [
    {
      title: "Заказ"
      id: "order"
      location: "order#index" # Supersonic module#view type navigation
    }
    {
      title: "История"
      id: "history"
      location: "history#index"
    }
    {
      title: "О приложение"
      id: "about"
      location: "about#index"
    }
  ]

  # rootView:
  #   location: "example#getting-started"

  preloads: [
    {
      id: "result"
      location: "history#result"
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
