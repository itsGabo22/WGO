import React from "react";
import { ArrowRight, ArrowLeft, ArrowUp } from "lucide-react";
import { cn } from "@/lib/utils";

interface NextTurnIndicatorProps {
  instruction: string;
  distance: string;
  turnDirection: "left" | "right" | "straight";
  className?: string;
}

export function NextTurnIndicator({ instruction, distance, turnDirection, className }: NextTurnIndicatorProps) {
  const Icon = turnDirection === "right" ? ArrowRight : turnDirection === "left" ? ArrowLeft : ArrowUp;

  return (
    <div className={cn("flex flex-col items-center justify-center text-black px-2", className)}>
      <div className="flex items-end justify-center gap-1">
        <Icon size={44} strokeWidth={3} className="text-primary mr-1" />
        <span className="text-[34px] font-bold leading-none tracking-tighter">{distance}</span>
      </div>
      <h2 className="mt-1 text-center text-[15px] font-bold leading-tight tracking-tight max-w-[240px] line-clamp-2">
        {instruction}
      </h2>
    </div>
  );
}
