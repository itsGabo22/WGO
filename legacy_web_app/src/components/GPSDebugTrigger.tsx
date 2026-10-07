"use client";

import React from "react";
import { cn } from "@/lib/utils";

interface GPSDebugTriggerProps {
  gpsLost: boolean;
  onToggle: () => void;
  className?: string;
}

export function GPSDebugTrigger({ gpsLost, onToggle, className }: GPSDebugTriggerProps) {
  return (
    <button 
      onClick={onToggle}
      className={cn(
        "absolute z-50 flex min-h-[48px] min-w-[48px] items-center justify-center p-2 text-xs text-white rounded-md transition-opacity",
        gpsLost ? "bg-[#BF3003] opacity-100" : "bg-black/20 opacity-50 hover:opacity-100",
        className
      )}
      aria-label={gpsLost ? "Restaurar señal GPS" : "Simular error de GPS"}
    >
      GPS
    </button>
  );
}
