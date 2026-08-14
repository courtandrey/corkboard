export const boardEvents = (owner: string | null): string =>
  owner ? `/api/v1/boards/${owner}/events` : "/api/v1/events";

export const boardEvent = (owner: string | null, id: string): string =>
  `${boardEvents(owner)}/${id}`;

export const notePath = (owner: string | null | undefined, id: string): string =>
  owner ? `/boards/${owner}/events/${id}` : `/events/${id}`;

export const boardPath = (owner: string | null | undefined): string =>
  owner ? `/boards/${owner}` : "/";

export const newNotePath = (owner: string | null): string =>
  owner ? `/boards/${owner}/new` : "/new";
