import type { Card, Player } from "../../protocol";

export function CardDetails({
  card,
  players,
  close,
}: {
  card: Card;
  players: Player[];
  close: () => void;
}) {
  const face = card.definition?.faces[card.face];

  return (
    <>
      <CardHeading card={card} close={close} />
      <p className="card-type">{face?.type || card.definition?.type}</p>
      <p className="rules-text">
        {face?.text ||
          card.definition?.text ||
          "This card’s identity is hidden."}
      </p>
      <small>
        Owner: {players[card.owner].name} · Controller:{" "}
        {players[card.controller].name}
      </small>
      <p className="eyebrow">
        {card.zone}
        {card.tapped ? " · TAPPED" : ""}
      </p>
    </>
  );
}

function CardHeading({ card, close }: { card: Card; close: () => void }) {
  const face = card.definition?.faces[card.face];
  const image = face?.image || card.definition?.image;
  const name = face?.name || card.definition?.name || "Face-down card";
  return (
    <>
      <div className="zone-heading">
        <h2>{name}</h2>
        <button aria-label="Close inspector" onClick={close}>
          ×
        </button>
      </div>
      {image && <img className="inspect-image" src={image} alt={name} />}
    </>
  );
}
