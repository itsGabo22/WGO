"use client";

import React from "react";
import { motion } from "framer-motion";
import { CheckCircle2 } from "lucide-react";
import { MassiveButton } from "./MassiveButton";

interface ScreenDestinationReachedProps {
  onDismiss: () => void;
}

export function ScreenDestinationReached({ onDismiss }: ScreenDestinationReachedProps) {
  return (
    <motion.div 
      className="flex h-full w-full flex-col items-center justify-center py-6 gap-6 relative"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.3 }}
    >
      <div className="flex flex-col items-center gap-3">
        <motion.div 
          className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-white mb-2"
          initial={{ scale: 0 }}
          animate={{ scale: [0, 1.2, 1] }}
          transition={{ duration: 0.5, ease: "easeOut", delay: 0.1 }}
        >
          <CheckCircle2 size={36} strokeWidth={2.5} />
        </motion.div>
        
        <h1 
          className="text-center text-[24px] font-bold leading-tight tracking-tight text-black max-w-[200px]"
          role="status"
          aria-live="polite"
        >
          Llegaste a tu destino
        </h1>
      </div>

      <MassiveButton 
        variant="inverted" 
        onClick={onDismiss}
        className="text-[13px] tracking-widest mt-2 px-8"
      >
        FINALIZAR
      </MassiveButton>
    </motion.div>
  );
}
