const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

export function parseMilliseconds(value: string | null | undefined): number | null {
  if (value == null || !/^-?\d+$/.test(value)) return null;
  const milliseconds = Number(value);
  return Number.isSafeInteger(milliseconds) ? milliseconds : null;
}

export function formatTime(
  timestamp: string | null | undefined,
  timeZone = browserTimeZone,
): string {
  const milliseconds = parseMilliseconds(timestamp);
  if (milliseconds == null || Math.abs(milliseconds) > 8640000000000000) return "-";
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(milliseconds));
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? "";
  return `${value("year")}-${value("month")}-${value("day")} ${value("hour")}:${value("minute")}:${value("second")}`;
}
