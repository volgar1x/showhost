package xyz.volgar1x.showhost

import com.typesafe.config.ConfigException
import xyz.volgar1x.showhost.crypto.b64decode

import zio.*
import zio.config.*
import zio.config.typesafe.*
import zio.http.URL
import zio.nio.file.{Files, Path => ZioPath}

import java.nio.file.Path
import java.time.ZoneId
import java.nio.file.LinkOption

sealed trait ContentLocation
object ContentLocation:
  final case class Local(directory: Path) extends ContentLocation
  final case class Remote(url: URL)       extends ContentLocation
  final case class Nginx(directory: Path) extends ContentLocation

case class ShowhostConfig(
    listenAddr: Option[String],
    listenPort: Option[Int],
    sessionKey: ShowhostConfig.Base64,
    timeZone: ZoneId,
    contentBase: URL,
    videoBase: URL,
    subtitleBase: URL,
    audioBase: URL,
    videoLocation: ContentLocation,
    videoOrigin: Path,
    audioLocation: ContentLocation,
    audioWriteLocation: Path,
    dataSourceClassName: String,
    dataSource: Map[String, String],
    tmdbApiKey: Option[String],
    libraryLanguages: Seq[String],
    videoCodecs: Seq[String],
    audioCodecs: Seq[String],
    musicCodec: String
)

object ShowhostConfig:
  case class Base64(bytes: Array[Byte])

  import magnolia.deriveConfigFromConfig
  given configBase16: Config[Base64]   = Config.string.map(s => Base64(s.b64decode))
  given configTimeZone: Config[ZoneId] = Config.string.mapAttempt(ZoneId.of(_))
  given configURL: Config[URL]         = Config.string.mapAttempt(URL.decode(_).fold(exc => throw exc, identity))
  given configPath: Config[Path]       = Config.string.map(Path.of(_))

  def apply(): URIO[ShowhostConfig, ShowhostConfig]           = ZIO.service[ShowhostConfig]
  def get[T](f: ShowhostConfig => T): URIO[ShowhostConfig, T] = ZIO.serviceWith[ShowhostConfig](f)

  def read(path: Path): IO[ConfigException | Config.Error, ShowhostConfig] =
    for
      provider <- ZIO
        .attempt(TypesafeConfigProvider.fromHoconFile(path.toFile()))
        .refineOrDie[ConfigException | Config.Error]:
          case exc: ConfigException => exc
          case err: Config.Error    => err
      config <- provider.load(magnolia.deriveConfig[ShowhostConfig])
      _ <- ZIO.logInfo(s"Loaded config at ${path}")
    yield config

  def fromEnv(varName: String = "SHOWHOST_CONFIG_PATH"): IO[ConfigException | Config.Error | Option[Nothing] | SecurityException, ShowhostConfig] =
    for
      configPath <- System.env(varName)
        .tap: env =>
          ZIO.logDebug(s"Resolved environment $$$varName to $env")
        .someOrFail(None)
        .map(Path.of(_))
      _ <- Files
        .isRegularFile(ZioPath.fromJava(configPath))
        .tap:
          case true => ZIO.logDebug(s"Found file at $$$varName: $configPath")
          case false => ZIO.logError(s"$$$varName points to inexistent file: $configPath")
        .filterOrFail(identity)(None)
      config <- read(configPath)
    yield config

  def fromClasspath: IO[ConfigException | Config.Error, ShowhostConfig] =
    for
      provider <- ZIO
        .attempt(TypesafeConfigProvider.fromResourcePath())
        .refineOrDie[ConfigException | Config.Error]:
          case exc: ConfigException => exc
          case err: Config.Error    => err
      config <- provider.load(magnolia.deriveConfig[ShowhostConfig])
      _ <- ZIO.logInfo("Loaded config from classpath")
    yield config
