package xyz.volgar1x.showhost.rpc

import xyz.volgar1x.showhost.core.User
import xyz.volgar1x.showhost.crypto.Password
import xyz.volgar1x.showhost.persist.UserPersist
import xyz.volgar1x.showhost.transport.*
import xyz.volgar1x.showhost.{BaseEnvironment, ShowhostRpc}

import zio.*

object UserRpc extends ShowhostRpc[BaseEnvironment]:
  import UserServiceGrpc.*

  override val handlers = List(
    METHOD_GET_SELF_PROFILE.respondWithAuthZIO: (_, user) =>
      import GetSelfProfileResponse.*

      ZIO.succeed(
        GetSelfProfileResponse(
          UserProfile(
            user.id.value,
            user.profileData.value.name,
            user.profileData.value.pictureUrl,
            user.profileData.value.interfaceLang,
            user.profileData.value.preferredAudioLangs.getOrElse(Seq.empty),
            user.profileData.value.preferredSubtitleLangs.getOrElse(Seq.empty),
            user.profileData.value.preferVo
          ),
          Some(user.role == User.Role.admin),
          user.name
        )
      )
    ,
    METHOD_UPDATE_SELF_PROFILE.respondWithAuthZIO: (msg, user) =>
      import UpdateSelfProfileResponse.*

      for _ <- UserPersist.update(
          user.withProfile: p =>
            p.copy(
              name = msg.name.getOrElse(p.name),
              interfaceLang = msg.interfaceLang.getOrElse(p.interfaceLang),
              preferredAudioLangs = Some(msg.preferredAudioLangs),
              preferredSubtitleLangs = Some(msg.preferredSubtitleLangs),
              preferVo = msg.preferVo
            )
        )
      yield UpdateSelfProfileResponse().withSuccess(Success())
    ,
    METHOD_UPDATE_PASSWORD.respondWithAuthZIO: (msg, user) =>
      import UpdatePasswordResponse.*

      val status = for
        _           <- user.password.verify(msg.oldPassword).filterOrFail(identity)(Failure(1, Some("invalid password")))
        newPassword <- Password.fromClearText(msg.newPassword)
        _           <- UserPersist.update(user.copy(password = newPassword))
      yield Success()

      status.fold(Status.Failure(_), Status.Success(_)).map(UpdatePasswordResponse(_))
  )
