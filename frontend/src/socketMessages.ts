import { decodeServerMessage, type Snapshot } from "./protocol";
export interface MessageHandlers {
  receive: (snapshot: Snapshot) => void;
  presence: (online: number[]) => void;
  acknowledge: () => void;
  fail: (message: string) => void;
}
export function applyServerMessage(
  data: unknown,
  handlers: MessageHandlers,
): void {
  const message = decodeServerMessage(data);
  switch (message.type) {
    case "snapshot":
      handlers.receive(message.view);
      break;
    case "presence":
      handlers.presence(message.online);
      break;
    case "ack":
      handlers.receive(message.view);
      handlers.acknowledge();
      break;
    case "error":
      if (message.view) handlers.receive(message.view);
      handlers.acknowledge();
      handlers.fail(message.message || "Action failed");
      break;
  }
}
