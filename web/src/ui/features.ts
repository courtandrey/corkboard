import { useFeatures } from "../api/hooks";

export type FeatureKey = "ARE_USER_DETAILS_EDITABLE";

export function useFeature(key: FeatureKey): boolean {
  const { data } = useFeatures();
  return data?.flags?.[key] !== false;
}
