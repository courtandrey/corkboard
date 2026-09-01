export const SUBSCRIPTIONS = "subscriptions";

export type BoardRef = string | null;

export const isSubscriptions = (board: BoardRef | undefined): boolean => board === SUBSCRIPTIONS;

export const boardEvents = (board: BoardRef): string =>
  board === SUBSCRIPTIONS
    ? "/api/v1/subscriptions/events"
    : board
      ? `/api/v1/boards/${board}/events`
      : "/api/v1/events";

export const boardEvent = (board: BoardRef, id: string): string => `${boardEvents(board)}/${id}`;

export const notePath = (board: BoardRef | undefined, id: string): string =>
  board === SUBSCRIPTIONS
    ? `/subscriptions/events/${id}`
    : board
      ? `/boards/${board}/events/${id}`
      : `/events/${id}`;

export const boardPath = (board: BoardRef | undefined): string =>
  board === SUBSCRIPTIONS ? "/subscriptions" : board ? `/boards/${board}` : "/";

export const newNotePath = (board: BoardRef): string =>
  board === SUBSCRIPTIONS ? "/new" : board ? `/boards/${board}/new` : "/new";
