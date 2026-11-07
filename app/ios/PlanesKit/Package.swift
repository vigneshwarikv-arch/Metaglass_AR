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
    // flights.json, scenarios.json and airports.json are copies of the files in
    // app/android/data/src/main/resources/; CI fails if they drift apart.
    .target(
      name: "PlanesData", dependencies: ["PlanesCore"],
      resources: [.copy("Resources/flights.json"), .copy("Resources/scenarios.json"), .copy("Resources/airports.json")]),
    .testTarget(name: "PlanesCoreTests", dependencies: ["PlanesCore"]),
    .testTarget(name: "PlanesDataTests", dependencies: ["PlanesData"]),
  ]
)
