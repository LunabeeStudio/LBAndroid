import Foundation
import SwiftUI
import iosDemo

struct KtorMonitoringDemoView: View {
    let demoRemoteDatasource = DemoRemoteDatasource(monitoring: DemoMonitoring.shared)
    @State private var dogFact = "Click the button to display a new fact!"

    var body: some View {
        List {
            Text(dogFact)
            Button("Get dog fact!") {
                Task {
                    try await demoRemoteDatasource.refreshDogFact()
                }
            }
            Button("Get a 404") {
                Task {
                    try await demoRemoteDatasource.refreshDogFact404()
                }
            }
            NavigationLink(destination: LBMonitoringView()) {
                Text("Consult logs (CMP)")
            }
        }
        .task {
            for try await dogFact in demoRemoteDatasource.dogFacts().asAsyncSequence() {
                self.dogFact = dogFact
            }
        }
    }
}
