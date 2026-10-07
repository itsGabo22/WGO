import React from "react";
import { cn } from "@/lib/utils";

interface StatusBadgeProps {
  children: React.ReactNode;
  className?: string;
  variant?: "neutral" | "primary";
}

export function StatusBadge({ children, className, variant = "neutral" }: StatusBadgeProps) {
  return (
    <div
      className={cn(
        "inline-flex items-center justify-center rounded-full px-[10px] py-[4px]",
        "text-[11px] font-bold tracking-[0.08em] uppercase", // label-md
        variant === "neutral" && "bg-surface text-black",
        variant === "primary" && "bg-primary text-white",
        className
      )}
    >
      {children}
    </div>
  );
}
