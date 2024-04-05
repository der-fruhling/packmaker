plugins {
    id("net.derfruhling.packmaker")
}

packageMetadata {
    version = "3.0.4"
    name = "Test Project"

    summary = """
        This is a test project.
        
        Nothing to see here!
    """.trimIndent()
}

dependencies {
    minecraft(":minecraft:1.20.4")
    minecraft(":fabric-loader:0.15.9")

    infix fun String.version(version: String) = "maven.modrinth:$this:$version"

    mod("fabric-api" version "0.96.4+1.20.4")
    mod("lithium" version "mc1.20.4-0.12.1")
}
