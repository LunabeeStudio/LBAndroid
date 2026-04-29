import UIKit
import SwiftUI
import iosDemo

struct LBMonitoringControllerRepresentable: UIViewControllerRepresentable {
    let navBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        DemoMonitoringControllerFactory.shared.create(
            monitoring: DemoMonitoring.shared,
            closeMonitoring: navBack
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct LBMonitoringView: View {
    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        LBMonitoringControllerRepresentable(
            navBack: { presentationMode.wrappedValue.dismiss() }
        )
        .ignoresSafeArea(.keyboard)
        .edgesIgnoringSafeArea(.all)
        .navigationBarBackButtonHidden(true)
    }
}
