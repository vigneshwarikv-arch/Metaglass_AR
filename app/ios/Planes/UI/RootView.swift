import SwiftUI

struct RootView: View {
  @EnvironmentObject var model: AppModel

  var body: some View {
    VStack(spacing: 0) {
      if let answer = model.lastAnswer {
        AnswerBanner(answer: answer) { model.lastAnswer = nil }.padding(.vertical, 6)
      }
      TabView {
        NavigationStack { NearbyView() }
          .tabItem { Label("Nearby", systemImage: "mappin.and.ellipse") }
        NavigationStack { PointView() }
          .tabItem { Label("Point", systemImage: "scope") }
        NavigationStack { SettingsView() }
          .tabItem { Label("Settings", systemImage: "gearshape") }
      }
      .tint(.sky)
    }
  }
}
