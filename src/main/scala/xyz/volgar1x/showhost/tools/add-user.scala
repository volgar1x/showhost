package xyz.volgar1x.showhost.tools

import io.getquill.JsonValue
import xyz.volgar1x.showhost.ULID
import xyz.volgar1x.showhost.core.{User, UserProfile}
import xyz.volgar1x.showhost.crypto.Password
import xyz.volgar1x.showhost.persist.UserPersist
import xyz.volgar1x.showhost.util.getpass

import zio.stream.ZStream
import zio.{Console, URIO}

private def readPassword(prompt: String) =
  getpass(prompt).orDie.flatMap(Password.fromClearText(_))

private def readBoolean(prompt: String) =
  ZStream
    .repeatZIO(Console.readLine(s"$prompt [yN]"))
    .takeWhile(x => x.nonEmpty && x != "y")
    .runLast
    .map(_.contains("y"))
    .orDie

def addUser(): URIO[UserPersist, Unit] =
  for
    name        <- Console.readLine("Enter user login:").orDie
    password    <- readPassword("Enter password:")
    profileName <- Console.readLine("Enter user name:").orDie
    isAdmin     <- readBoolean("Is admin?")
    userId      <- ULID.random
    user = User(
      id = userId,
      name = name,
      password = password,
      profileData = JsonValue(
        UserProfile(
          name = profileName,
          pictureUrl = "",
          interfaceLang = "fr",
          preferredAudioLangs = None,
          preferredSubtitleLangs = None,
          preferVo = None
        )
      ),
      role = if isAdmin then User.Role.admin else User.Role.user
    )
    _ <- UserPersist.create(user)
  yield ()

def updateUserPassword(): URIO[UserPersist, Unit] =
  (for
    name        <- Console.readLine("Enter user login:").orDie
    user        <- UserPersist.findByName(name).someOrFail(Exception("no such user"))
    newPassword <- readPassword("Enter new password:")
    _           <- UserPersist.update(user.copy(password = newPassword))
  yield ()).catchAll: exc =>
    Console.printLine(exc.getMessage()).orDie
