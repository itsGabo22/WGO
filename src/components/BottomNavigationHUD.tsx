"use client";

import React, { useState } from "react";
import { Volume2, VolumeX } from "lucide-react";
import { StatusBadge } from "./StatusBadge";
import { motion } from "framer-motion";

interface BottomNavigationHUDProps {
  eta: string;
  totalDistance: string;
}

export function BottomNavigationHUD({ eta, totalDistance }: BottomNavigationHUDProps) {
  const [isMuted, setIsMuted] = useState(false);

  return (
    <div className="flex w-full items-center justify-between px-8 py-2">
      <div className="flex flex-col items-start gap-1">
        <span className="text-[14px] font-bold tracking-tight text-black">{eta}</span>
        <span className="text-[12px] font-semibold text-neutral-500">{totalDistance}</span>
      </div>
      
      <motion.button
        onClick={() => setIsMuted(!isMuted)}
        className="flex h-[48px] w-[48px] items-center justify-center rounded-full bg-surface border-[1.5px] border-black text-black"
        whileTap={{ scale: 0.9 }}
      >
        {isMuted ? <VolumeX size={20} strokeWidth={2.5} /> : <Volume2 size={20} strokeWidth={2.5} />}
      </motion.button>
    </div>
  );
}
