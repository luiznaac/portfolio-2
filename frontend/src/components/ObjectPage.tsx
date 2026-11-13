import { Outlet, useLocation } from "react-router-dom";
import { OBJECTS, type ObjectKey, activeSub } from "../lib/routes.ts";
import { ObjectTabs } from "./ObjectTabs.tsx";
import { PageHead } from "./PageHead.tsx";

// One object's page frame: the object title, the active sub-view's reserved subtitle line and the
// page-tab strip, with the routed sub-view below. The tabs never appear in the sidebar.
export function ObjectPage({ objectKey }: { objectKey: ObjectKey }) {
  const object = OBJECTS.find((candidate) => candidate.key === objectKey);
  if (object === undefined) throw new Error(`unknown object ${objectKey}`);

  const { pathname } = useLocation();
  const active = activeSub(object, pathname);

  return (
    <>
      <PageHead title={object.label} subtitle={active.subtitle} />
      <ObjectTabs objectKey={objectKey} />
      <Outlet />
    </>
  );
}
