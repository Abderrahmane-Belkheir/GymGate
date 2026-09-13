import { useEffect, useRef, useState, type ComponentPropsWithoutRef, type ElementType, type ReactNode } from "react";

interface RevealOnScrollOwnProps {
  children: ReactNode;
  delay?: number;
  className?: string;
  as?: ElementType;
}

type RevealOnScrollProps = RevealOnScrollOwnProps &
  Omit<ComponentPropsWithoutRef<"div">, keyof RevealOnScrollOwnProps>;

/**
 * Fades + lifts children into place the first time they cross into the
 * viewport. Pure CSS transition (see `.reveal` in base.css) driven by one
 * class toggle — cheap, and a no-op visually when reduced motion is on
 * because the CSS rule for that already neutralises the transform/opacity.
 * Any extra prop (role, aria-*, data-*) passes straight through, so it can
 * wrap ARIA table markup without losing semantics.
 */
export default function RevealOnScroll({
  children,
  delay = 0,
  className = "",
  as: Tag = "div",
  ...rest
}: RevealOnScrollProps) {
  const ref = useRef<HTMLElement | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.14, rootMargin: "0px 0px -8% 0px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <Tag
      ref={ref}
      className={`reveal${visible ? " is-visible" : ""}${className ? ` ${className}` : ""}`}
      style={delay ? { transitionDelay: `${delay}ms` } : undefined}
      {...rest}
    >
      {children}
    </Tag>
  );
}
