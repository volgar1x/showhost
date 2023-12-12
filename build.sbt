name := "showhost-backend"
scalacOptions := Seq(
  "-encoding",
  "utf8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Ykind-projector",
  "-Wvalue-discard",
  "-Wunused:implicits",
  "-Wunused:explicits",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:params",
  "-Wunused:privates",
  "-Xfatal-warnings"
)
Compile / console / scalacOptions -= "-Wunused:imports"

libraryDependencies ++= Seq(
  "ch.qos.logback"        % "logback-classic"     % "1.4.11" % Runtime,
  "com.thesamet.scalapb" %% "scalapb-json4s"      % "0.12.0",
  "dev.zio"              %% "zio"                 % "2.0.17",
  "dev.zio"              %% "zio-cli"             % "0.5.0",
  "dev.zio"              %% "zio-config"          % "4.0.0-RC16",
  "dev.zio"              %% "zio-config-magnolia" % "4.0.0-RC16",
  "dev.zio"              %% "zio-config-refined"  % "4.0.0-RC16",
  "dev.zio"              %% "zio-config-typesafe" % "4.0.0-RC16",
  "dev.zio"              %% "zio-http"            % "3.0.0-RC2",
  "dev.zio"              %% "zio-json"            % "0.6.2",
  "dev.zio"              %% "zio-logging"         % "2.1.14",
  "dev.zio"              %% "zio-logging-slf4j"   % "2.1.14",
  "dev.zio"              %% "zio-nio"             % "2.0.2",
  "dev.zio"              %% "zio-test"            % "2.0.17" % Test,
  "dev.zio"              %% "zio-test-magnolia"   % "2.0.17" % Test,
  "dev.zio"              %% "zio-test-sbt"        % "2.0.17" % Test,
  "io.azam.ulidj"         % "ulidj"               % "1.0.4",
  "io.getquill"          %% "quill-jdbc-zio"      % "4.6.0",
  "io.getquill"          %% "quill-zio"           % "4.6.0",
  "net.java.dev.jna"      % "jna"                 % "5.13.0",
  "org.postgresql"        % "postgresql"          % "42.3.1",
  "org.slf4j"             % "slf4j-api"           % "1.7.36"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

Compile / mainClass := Some("xyz.volgar1x.showhost.ShowhostApp")

console / initialCommands := """import zio.*
import Runtime.default.*
import Unsafe.unsafely
def runZIO[E, A](expr: ZIO[Any, E, A]): Exit[E, A] = unsafely(unsafe.run(expr))
"""

run / fork           := true
run / connectInput   := true
run / outputStrategy := Some(StdoutOutput)
run / envVars        := Map("quill_query_tooLong" -> "0", "quill_binds_log" -> "true")
run / javaOptions := {
  val old = javaOptions.value
  if (Option(System.getProperty("java.vendor")).exists(_.startsWith("GraalVM")))
    "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image" +: old
  else
    old
}

graalVMNativeImageOptions ++= Seq(
  "-J-Dfile.encoding=UTF-8",
  "--no-fallback",
  "--install-exit-handlers",
  "--enable-http",
  "--allow-incomplete-classpath",
// slf4j {{{
  "--initialize-at-build-time=org.slf4j.LoggerFactory",
  "--initialize-at-build-time=org.slf4j.impl.StaticLoggerBinder",
  "--initialize-at-build-time=org.slf4j.jul",
  "--initialize-at-build-time=org.slf4j.simple.SimpleLogger",
// }}}
// netty {{{
  "--initialize-at-run-time=io.netty.util.internal.logging.Log4JLogger",
  "--initialize-at-run-time=io.netty.util.AbstractReferenceCounted",
  "--initialize-at-run-time=io.netty.channel.DefaultFileRegion",
  "--initialize-at-run-time=io.netty.channel.epoll",
  "--initialize-at-run-time=io.netty.channel.kqueue",
  "--initialize-at-run-time=io.netty.channel.unix",
  "--initialize-at-run-time=io.netty.handler.ssl",
  "--initialize-at-run-time=io.netty.incubator.channel.uring",
// }}}
// logback {{{
  "--initialize-at-build-time=ch.qos.logback.classic.Level",
  "--initialize-at-build-time=ch.qos.logback.classic.Logger",
  "--initialize-at-build-time=ch.qos.logback.classic.PatternLayout",
  "--initialize-at-build-time=ch.qos.logback.core.CoreConstants",
  "--initialize-at-build-time=ch.qos.logback.core.status.InfoStatus",
  "--initialize-at-build-time=ch.qos.logback.core.util.Loader",
  "--initialize-at-build-time=ch.qos.logback.core.util.StatusPrinter",
  "--initialize-at-build-time=ch.qos.logback.core.util.Duration",
  "--initialize-at-build-time=ch.qos.logback.core.subst.Token",
  "--initialize-at-build-time=ch.qos.logback.core.pattern.parser.TokenStream$1",
  "--initialize-at-build-time=ch.qos.logback.core.model.processor.ChainedModelFilter$1",
  "--initialize-at-build-time=ch.qos.logback.core.model.processor.DefaultProcessor$1",
  "--initialize-at-build-time=ch.qos.logback.core.model.processor.ImplicitModelHandler$1",
  "--initialize-at-build-time=ch.qos.logback.core.subst.NodeToStringTransformer$1",
  "--initialize-at-build-time=ch.qos.logback.core.pattern.parser.Parser",
  "--initialize-at-build-time=ch.qos.logback.core.subst.Tokenizer$1",
  "--initialize-at-build-time=ch.qos.logback.core.subst.Parser$1",
  "--initialize-at-build-time=ch.qos.logback.classic.model.processor.LogbackClassicDefaultNestedComponentRules"
// }}}
)

graalVMNativeImageCommand := {
  Option(System.getenv("GRAALCE_HOME")).map(file(_)).map(_ / "bin" / "native-image").map(_.toString).getOrElse("native-image")
}

// graalVMNativeImageOptions := {
//   val old = graalVMNativeImageOptions.value
//   System.getProperty("os.name") match {
//     case "Mac OS X" => old
//     case _ =>
//       val opts = Seq("--static", "--libc=musl", "--target=linux-aarch64", "-H:-CheckToolchain")
//       opts ++ old
//   }
// }

Compile / doc / sources := Seq.empty

semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
