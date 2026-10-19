type CardBounds = Pick<
  DOMRect,
  "left" | "right" | "top" | "bottom" | "width" | "height"
>;

export function artworkPlacement(
  card: CardBounds,
  width: number,
  height: number,
) {
  const margin = 8;
  if (outsideViewport(card, width, height)) return null;
  const candidates = [
    {
      x: card.right + margin,
      y: margin,
      w: width - card.right - margin * 2,
      h: height - margin * 2,
      side: "right",
    },
    {
      x: margin,
      y: margin,
      w: card.left - margin * 2,
      h: height - margin * 2,
      side: "left",
    },
    {
      x: margin,
      y: card.bottom + margin,
      w: width - margin * 2,
      h: height - card.bottom - margin * 2,
      side: "below",
    },
    {
      x: margin,
      y: margin,
      w: width - margin * 2,
      h: card.top - margin * 2,
      side: "above",
    },
  ];
  for (const space of candidates) {
    const w = Math.min(280, space.w, space.h / 1.4);
    const h = w * 1.4;
    if (w <= card.width || h <= card.height) continue;
    const left =
      space.side === "left"
        ? card.left - margin - w
        : space.side === "right"
          ? space.x
          : Math.max(margin, Math.min(card.left, width - margin - w));
    const top =
      space.side === "above"
        ? card.top - margin - h
        : space.side === "below"
          ? space.y
          : Math.max(margin, Math.min(card.top, height - margin - h));
    return { left, top, width: w, height: h };
  }
  return null;
}

function outsideViewport(card: CardBounds, width: number, height: number) {
  return (
    card.bottom <= 0 ||
    card.top >= height ||
    card.right <= 0 ||
    card.left >= width
  );
}
