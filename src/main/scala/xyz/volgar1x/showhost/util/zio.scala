package xyz.volgar1x.showhost.util

import zio.{Duration, ZIO}

import java.time.{Instant, LocalDate, LocalTime, OffsetDateTime, ZoneOffset}

extension [R, E, A](zio: ZIO[R, E, A])
  def delayUntil(dt: OffsetDateTime): ZIO[R, E, A] =
    zio.delay(Duration.fromInterval(Instant.now(), dt.toInstant()))

  def delayUntilNextTime(t: LocalTime, off: ZoneOffset = ZoneOffset.UTC): ZIO[R, E, A] =
    delayUntil(OffsetDateTime.of(LocalDate.now().plusDays(1), t, off))
