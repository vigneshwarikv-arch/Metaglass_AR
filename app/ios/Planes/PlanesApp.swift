import MWDATCore
import SwiftUI

@main
struct PlanesApp: App {
  @StateObject private var model: AppModel
  @Environment(\.scenePhase) private var scenePhase

  init() {
    // Meta's glasses SDK must be configured once at launch, before anything else uses it.
    do {
      try Wearables.configure()
    } catch {
      print("Wearables.configure failed: \(error)")
    }
    _model = StateObject(wrappedValue: AppModel())
  }

  var body: some Scene {
    WindowGroup {
      RootView()
        .environmentObject(model)
        .onOpenURL { url in
          // Meta AI calls back here after registration / permission flows.
          guard let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
            items.contains(where: { $0.name == "metaWearablesAction" })
          else { return }
          Task { _ = try? await Wearables.shared.handleUrl(url) }
        }
        .onChange(of: scenePhase) { _, phase in
          model.setVisible(phase == .active)
        }
    }
  }
}
