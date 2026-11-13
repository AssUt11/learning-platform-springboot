import { useLayoutEffect } from "react";

// Animate the committed page, never the route update or the persistent Agent instance.
export default function usePageMotion(shellRef, routeKey) {
  useLayoutEffect(() => {
    const shell = shellRef.current;
    const surface = shell && [...shell.children].find(
      (element) => element.tagName !== "HEADER" && !element.hidden
    );
    if (!surface) return;
    surface.setAttribute("data-route-surface", "");
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let animation;
    try {
      const tokens = window.getComputedStyle(shell);
      // Opacity keeps video, graph coordinates and fixed-position controls stable.
      animation = surface.animate?.([{ opacity: 0.72 }, { opacity: 1 }], {
        duration: Number.parseFloat(tokens.getPropertyValue("--motion-page")) || 180,
        easing: tokens.getPropertyValue("--ease-out").trim() || "ease-out"
      });
    } catch {
      // A missing or faulty animation API must never block navigation.
    }
    return () => animation?.cancel();
  }, [shellRef, routeKey]);
}
