package xyz.volgar1x.showhost

import io.grpc.MethodDescriptor
import scalapb.json4s.{JsonFormat, JsonFormatException}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import xyz.volgar1x.showhost.core.User
import xyz.volgar1x.showhost.crypto.SecureToken
import xyz.volgar1x.showhost.persist.UserPersist

import zio.*
import zio.http.*

import java.nio.charset.StandardCharsets.UTF_8

type BaseEnvironment = Option[SecureToken] & ShowhostConfig & UserPersist

trait ShowhostRpc[-R]:
  self =>
  import ShowhostRpc.*

  def handlers: Seq[HandlerRef[R, ?, ?]]

  def ++[R1 <: R](other: ShowhostRpc[R1]): ShowhostRpc[R1] = new ShowhostRpc[R1]:
    override def handlers = self.handlers ++ other.handlers

  extension [A <: GeneratedMessage, B <: GeneratedMessage](
      method: MethodDescriptor[A, B]
  )(using request: GeneratedMessageCompanion[A], response: GeneratedMessageCompanion[B])
    inline def serviceName: String = method.getServiceName()
    inline def methodName: String  = method.getBareMethodName()

    def respondZIO[R](h: Handler[R, A, B]): HandlerRef[R, A, B] =
      HandlerRef(method.serviceName, method.methodName, request, response, h)

    def respondWithAuthZIO[R](
        h: Handler[R, (A, User), B]
    ): HandlerRef[R & BaseEnvironment, A, B] =
      method.respondZIO: request =>
        for
          config <- ShowhostConfig()
          token  <- ZIO.service[Option[SecureToken]].some.orDieWith(_ => HttpError.Forbidden())
          userId <- SecureToken
            .decrypt[ULID](token, config.sessionKey.bytes)(using summon, config.timeZone)
            .orDieWith(_ => HttpError.Forbidden())
          user     <- UserPersist.get(userId).orDieWith(_ => HttpError.Forbidden())
          response <- h(request, user)
        yield response

    def respondWithAuthOnlyRoleZIO[R](role: User.Role)(h: Handler[R, (A, User), B]): HandlerRef[R & BaseEnvironment, A, B] =
      respondWithAuthZIO: (msg, user) =>
        if user.role >= role
        then h(msg, user)
        else ZIO.die(HttpError.Unauthorized())

  extension [R, A, B](self: Handler[R, A, B])
    def +:[R2 <: R, A2 <: A, B2 >: B](other: List[Handler[R2, A2, B2]]): List[Handler[R & R2, A & A2, B | B2]] =
      self :: other

object ShowhostRpc:
  type Handler[-R, -A, +B] = A => ZIO[R, Nothing, B]

  case class HandlerRef[-R, A <: GeneratedMessage, B <: GeneratedMessage](
      service: String,
      method: String,
      request: GeneratedMessageCompanion[A],
      response: GeneratedMessageCompanion[B],
      func: Handler[R, A, B]
  )

  val mediaProtobuf: MediaType   = MediaType("application", "protobuf", true, true)
  val mediaJson: MediaType       = MediaType.application.json
  val respondsTo: Set[MediaType] = Set(mediaProtobuf, mediaJson)

  private def sendResponse[R](request: Request, handler: HandlerRef[R, ?, ?]) =
    val contentType = request.header(Header.ContentType).map(_.mediaType).filter(respondsTo).getOrElse(mediaProtobuf)
    val accept =
      request
        .header(Header.Accept)
        .map(_.mimeTypes.toList)
        .getOrElse(Nil)
        .sortBy(_.qFactor.getOrElse(1.0))
        .iterator
        .map(_.mediaType)
        .filter(respondsTo)
        .nextOption()
        .getOrElse(contentType)
    val token = request
      .header(Header.Authorization)
      .collect:
        case Header.Authorization.Bearer(token) => SecureToken(token)

    (for
      body <- request.body.asArray
      msg <- contentType match
        case `mediaProtobuf` => ZIO.attempt(handler.request.parseFrom(body)).mapError(exc => HttpError.BadRequest(exc.getMessage()))
        case `mediaJson` =>
          ZIO
            .attempt(JsonFormat.fromJsonString(String(body, UTF_8))(using handler.request))
            .mapError:
              case exc: JsonFormatException => HttpError.BadRequest(exc.getMessage())
              case _                        => HttpError.BadRequest()
        case _ => ZIO.fail(HttpError.BadRequest(s"Content-Type not handled: ${contentType.fullType}"))
      msg2 <- handler.func(msg).provideSomeLayer(ZLayer.succeed(token))
    yield Response(
      headers = Headers(Header.ContentType(accept)),
      body = accept match
        case `mediaProtobuf` => Body.fromChunk(Chunk.fromArray(handler.response.toByteArray(msg2)))
        case `mediaJson`     => Body.fromString(JsonFormat.toJsonString(msg2))
        case _               => throw IllegalStateException()
    ))
      .catchAllCause:
        case Cause.Fail(HttpError(status, msg), _) =>
          ZIO.succeed(Response(status = status, body = Body.fromString(msg)))
        case Cause.Die(HttpError(status, msg), _) =>
          ZIO.succeed(Response(status = status, body = Body.fromString(msg)))
        case cause =>
          for _ <- ZIO.logErrorCause("Unhandled error", cause)
          yield Response(Status.InternalServerError)

  def httpV2[R](rpc: ShowhostRpc[R]): Http[R, Nothing, Request, Response] =
    val routes = rpc.handlers
      .map(h => (h.service.split('.').last, h.method) -> h)
      .toMap
    Http.collectZIO[Request]:
      case request @ Method.POST -> Root / "v2" / service / method =>
        routes.get(service, method).orElse(service.split('.').lastOption.flatMap(routes.get(_, method))) match
          case Some(handler) => sendResponse(request, handler)
          case None          => ZIO.succeed(Response(Status.NotFound))
