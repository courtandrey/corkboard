import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";

interface Frame {
  type: string;
  payload: { conversationId?: string };
}

export function useSocket(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    let socket: WebSocket | null = null;
    let closed = false;
    let attempt = 0;
    let reconnectTimer: number | undefined;

    const connect = () => {
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      socket = new WebSocket(`${protocol}://${window.location.host}/ws`);

      socket.onopen = () => {
        attempt = 0;
      };
      socket.onmessage = (event) => {
        let frame: Frame;
        try {
          frame = JSON.parse(String(event.data)) as Frame;
        } catch {
          return;
        }
        switch (frame.type) {
          case "notification:new":
            void queryClient.invalidateQueries({ queryKey: ["notifications"] });
            break;
          case "message:new":
            void queryClient.invalidateQueries({ queryKey: ["conversations"] });
            if (frame.payload.conversationId) {
              void queryClient.invalidateQueries({
                queryKey: ["messages", frame.payload.conversationId],
              });
            }
            break;
          case "conversation:read":
            if (frame.payload.conversationId) {
              void queryClient.invalidateQueries({
                queryKey: ["messages", frame.payload.conversationId],
              });
            }
            break;
        }
      };
      socket.onclose = () => {
        if (closed) return;
        const delay = Math.min(1000 * 2 ** attempt, 30_000);
        attempt += 1;
        reconnectTimer = window.setTimeout(connect, delay);
      };
    };

    connect();
    return () => {
      closed = true;
      window.clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, [enabled, queryClient]);
}
