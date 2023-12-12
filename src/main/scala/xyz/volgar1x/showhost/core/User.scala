package xyz.volgar1x.showhost.core

import io.getquill.{JsonValue, MappedEncoding, SchemaMeta, schemaMeta}
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.crypto.Password

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder, SnakeCase, jsonMemberNames}

import java.time.LocalDateTime

@jsonMemberNames(SnakeCase)
case class UserProfile(
    name: String,
    pictureUrl: String,
    interfaceLang: String,
    preferredAudioLangs: Option[Seq[String]] = None,
    preferredSubtitleLangs: Option[Seq[String]] = None,
    preferVo: Option[Boolean] = None
)

case class User(
    id: ULID,
    name: String,
    password: Password,
    profileData: JsonValue[UserProfile],
    role: User.Role
) {
  def withProfile(f: UserProfile => UserProfile): User =
    copy(profileData = JsonValue(f(profileData.value)))
}

object User:
  enum Role extends Ordered[Role]:
    def compare(that: Role): Int = ordinal - that.ordinal
    case user, admin

  implicit val passwordEncoding: MappedEncoding[Password, Array[Byte]] = MappedEncoding(_.hash)
  implicit val passwordDecoding: MappedEncoding[Array[Byte], Password] = MappedEncoding(Password(_))

  implicit val roleEncoding: MappedEncoding[User.Role, String] = MappedEncoding(_.toString())
  implicit val roleDecoding: MappedEncoding[String, User.Role] = MappedEncoding(User.Role.valueOf(_))

  implicit val profileEncoder: JsonEncoder[UserProfile] = DeriveJsonEncoder.gen
  implicit val profileDecoder: JsonDecoder[UserProfile] = DeriveJsonDecoder.gen

case class ViewHistory(
    id: ULID,
    userId: ULID,
    videoId: ULID,
    viewedAt: LocalDateTime,
    progress: Int
)

object ViewHistory:
  inline given SchemaMeta[ViewHistory] = schemaMeta("view_history")

case class Playlist(
    id: ULID,
    userId: ULID,
    name: String
)
