package xyz.volgar1x.showhost.rpc

import xyz.volgar1x.showhost.crypto.SecureToken
import xyz.volgar1x.showhost.persist.UserPersist
import xyz.volgar1x.showhost.transport.*
import xyz.volgar1x.showhost.{BaseEnvironment, ShowhostConfig, ShowhostRpc}

import zio.*

import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

object UserAuthRpc extends ShowhostRpc[BaseEnvironment]:
  import UserAuthServiceGrpc.*

  private val authenticate = METHOD_AUTHENTICATE.respondZIO: msg =>
    import UserAuthResponse.*

    val status =
      for
        config <- ShowhostConfig()
        user   <- UserPersist.findByName(msg.username).someOrFail(Failure(1, Some("invalid username")))
        _      <- user.password.verify(msg.password).filterOrFail(identity)(Failure(1, Some("invalid password")))
        token  <- SecureToken.encrypt(user.id, config.sessionKey.bytes, 30.days)(using summon, config.timeZone)
      yield Success(token = token._1.encoded, expires = token._2.format(ISO_OFFSET_DATE_TIME))

    status.fold(Status.Failure(_), Status.Success(_)).map(UserAuthResponse(_))

  private val refreshToken = METHOD_REFRESH_TOKEN.respondWithAuthZIO: (_, user) =>
    import RefreshTokenResponse.*

    for
      config <- ShowhostConfig()
      token  <- SecureToken.encrypt(user.id, config.sessionKey.bytes, 30.days)(using summon, config.timeZone)
    yield RefreshTokenResponse().withSuccess(Success(token = token._1.encoded, expires = token._2.format(ISO_OFFSET_DATE_TIME)))

  private val forgotPassword = METHOD_FORGOT_PASSWORD.respondZIO: _ =>
    import ForgotPasswordResponse.*
    ZIO.succeed(ForgotPasswordResponse().withFailure(Failure(501, Some("not implemented"))))

  override val handlers = List(authenticate, refreshToken, forgotPassword)
