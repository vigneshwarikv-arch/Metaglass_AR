// Kept free of SwiftUI on purpose: MWDATDisplay has its own Text/Button/Image types.
import Foundation
import MWDATDisplay
import PlanesCore

/// The small answer card shown on Meta Ray-Ban Display.
enum DisplayCard {
  static func send(_ card: AnswerCard, to display: Display) async throws {
    let route = card.route ?? ""
    let meta = card.meta ?? ""
    try await display.send(
      FlexBox(direction: .column, spacing: 8) {
        Text(card.title, style: .heading)
        if !route.isEmpty {
          Text(route, style: .body)
        }
        if !meta.isEmpty {
          Text(meta, style: .body, color: .secondary)
        }
      }
      .padding(24)
      .background(.card)
    )
  }
}
