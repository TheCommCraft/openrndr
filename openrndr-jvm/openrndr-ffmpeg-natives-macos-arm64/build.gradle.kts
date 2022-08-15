plugins {
    org.openrndr.convention.`kotlin-jvm`
}
dependencies {
    runtimeOnly(libs.bundles.javacpp.ffmpeg) {
        artifact {
            classifier = "macosx-arm64"
            extension = DependencyArtifact.DEFAULT_TYPE
        }
    }
}