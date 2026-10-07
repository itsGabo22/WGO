"use client";

import React from "react";
import { VoiceToggle } from "./VoiceToggle";

interface BottomNavigationHUDProps {
  eta: string;
  totalDistance: string;
}

export function BottomNavigationHUD({ eta, totalDistance }: BottomNavigationHUDProps) {
  return (
    <div className="flex w-full items-center justify-between px-8 py-2">
      <div className="flex flex-col items-start gap-1">
        <span className="text-[14px] font-bold tracking-tight text-black">{eta}</span>
        <span className="text-[12px] font-semibold text-neutral-500">{totalDistance}</span>
      </div>
      
      <VoiceToggle />
    </div>
  );
}
