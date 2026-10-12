// swift-tools-version:5.9
// The engine for the iOS app: a Swift port of app/android/core and app/android/data.
// Pure Swift + Foundation (no UIKit), so `swift test` runs on any Mac without a simulator.
import PackageDescription

let package = Package(
  name: "PlanesKit",
  platforms: [.iOS(.v17), .macOS(.v13)],
  products: [
    .library(name: "PlanesCore", targets: ["PlanesCore"]),
    .library(name: "PlanesData", targets: ["PlanesData"]),
  ],
  targets: [
    .target(name: "PlanesCore"),
    // flights.json is a copy of app/android/data/src/main/resources/sample/flights.json;
    // CI fails if the two drift apart.
    .target(name: "PlanesData", dependencies: ["PlanesCore"], resources: [.copy("Resources/flights.json")]),
    .testTarget(name: "PlanesCoreTests", dependencies: ["PlanesCore"]),
    .testTarget(name: "PlanesDataTests", dependencies: ["PlanesData"]),
  ]
)
