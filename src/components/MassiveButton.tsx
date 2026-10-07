import React from "react";
import { cn } from "@/lib/utils";

interface MassiveButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "inverted" | "secondary";
  children: React.ReactNode;
}

export function MassiveButton({ variant = "primary", children, className, ...props }: MassiveButtonProps) {
  return (
    <button
      className={cn(
        "flex items-center justify-center gap-2 rounded-full font-bold transition-all active:scale-95",
        "h-[52px] min-w-[140px] px-6 text-[16px]", // body-lg size basically
        variant === "primary" && "bg-primary text-white border-[1.5px] border-primary",
        variant === "inverted" && "bg-black text-white border-[1.5px] border-black",
        variant === "secondary" && "bg-surface text-black border-[1.5px] border-black", // secondary/cancel style
        className
      )}
      {...props}
    >
      {children}
    </button>
  );
}
