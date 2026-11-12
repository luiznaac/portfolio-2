import { OBJECTS, type ObjectKey } from "../lib/routes.ts";
import { PageTabs } from "./PageTabs.tsx";

// One object's page-tab strip. ObjectPage frames the object's routed sub-views with it, and a detail
// page that owns its own head (the frozen month) reuses it so the strip never drifts between the
// list and its detail route.
export function ObjectTabs({ objectKey }: { objectKey: ObjectKey }) {
  const object = OBJECTS.find((candidate) => candidate.key === objectKey);
  if (object === undefined) throw new Error(`unknown object ${objectKey}`);

  return (
    <PageTabs tabs={object.subs.map((sub) => ({ to: sub.path, label: sub.label, end: sub.end }))} />
  );
}
